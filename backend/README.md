# AI Pet household feedback service

Python 3.10 or newer; no pip packages. The service plans against a local checkout,
and calls the configured project-session API only after an authenticated device
approves the current plan revision. It never executes an implementation locally.

Run from the repository root:

```powershell
python -m unittest discover -s backend/tests -v
python -m backend.feedback_server --config /home/junte/.config/ai-pet-feedback/config.json
```

The tests use temporary SQLite databases, local HTTP servers, and injected planner,
dispatcher, and release verifier implementations. They do not dispatch remote work.

## Private configuration

Create `/home/junte/.config/ai-pet-feedback/config.json` outside the checkout,
owned by the service account with mode `0600`. Set real values privately; do not
commit this file, pairing codes, or project tokens. The exact JSON properties are:

| Property | Meaning / default |
| --- | --- |
| `database` | Required absolute SQLite path; recommended `/home/junte/.local/share/ai-pet-feedback/feedback.sqlite3` |
| `pairing_code` | Required household pairing code; use a long random value |
| `project_session_url` | Required exact HTTPS project-session creation endpoint |
| `project_token` | Required project-scoped Bearer credential, used only by the dispatcher |
| `public_base_url` | Required externally reachable API base, `https://home.sunny-string.cn/ai-pet-feedback` |
| `repository` | Required absolute local checkout path inspected by the planner |
| `listen` | Optional address, default `127.0.0.1:3057` |
| `planner_cli` | Optional Codex executable path, default `codex` |
| `planner_timeout` | Optional integer seconds, default `180`, range `1..600` |
| `dispatch_timeout` | Optional integer seconds, default `20`, range `1..60` |
| `path_prefix` | Optional prefix, default empty. Set `/ai-pet-feedback` when the reverse proxy retains that prefix. Both stripped and prefixed paths then work. |

The service account needs a working, authenticated Codex CLI and read access to
the configured repository. Linux additionally requires `bwrap` (bubblewrap) and
working unprivileged user/PID namespaces; planning fails closed if either is
unavailable. The configured Codex executable and its interpreter must be installed
under system-visible paths such as `/usr/local/bin` or `/usr/bin`, since personal
`~/.local/bin` and `~/.nvm` directories are hidden from the planner.

On Linux a bubblewrap namespace covers `/home` and `/tmp` with empty temporary
filesystems and provides a new `/proc` inside a separate PID/user namespace. It
then exposes only the configured repository read-only, `~/.codex` read-write for
Codex authentication/state, and one temporary result directory read-write. Parent
directories are created empty; the private service config, backups, deployment
code, and `~/.remote-agent` remain hidden. The schema is copied into the result
directory. The process starts inside the repository, and its parent service
process/environment are outside its PID namespace. Do not place project-service
secrets in the repository or `~/.codex`.

Within that isolation the planner invokes `codex exec --sandbox read-only
-c 'approval_policy="never"' -C REPOSITORY --output-schema RESULT_DIRECTORY/plan.schema.json`
with bounded output and a timeout. Planning output and child errors are never
returned to clients or printed. The project credential is not included in the
planner prompt or remote task message. Parent-facing plan fields are requested in
Simplified Chinese; technical identifiers and paths retain their original names.

Use `backend/ai-pet-feedback.service` as the systemd unit after deploying this
repository at `/home/junte/ai-pet-feedback`. If the deployment directory differs,
update `WorkingDirectory`; the private config path remains outside that directory.
Create the data and Codex directories before starting the unit:

```sh
install -d -m 700 /home/junte/.local/share/ai-pet-feedback /home/junte/.config/ai-pet-feedback
sudo systemctl daemon-reload
sudo systemctl enable --now ai-pet-feedback.service
curl --fail http://127.0.0.1:3057/health
```

Expose only the HTTPS reverse proxy; keep the backend bound to loopback. A typical
nginx location strips the public prefix:

```nginx
location /ai-pet-feedback/ {
    client_max_body_size 64k;
    proxy_pass http://127.0.0.1:3057/;
    proxy_set_header Host $host;
    proxy_read_timeout 60s;
}
```

## HTTP contract

All bodies are JSON objects, UTF-8, `Content-Type: application/json`, maximum
65,536 bytes. All responses disable caching. Errors contain a sanitized `error`
string. Invalid fields return 400; stale revisions/transitions 409; device auth
failures 401; ownership misses 404; large bodies 413; temporary failures 503.
Titles allow 80 characters; feedback messages allow 16,000 UTF-8 bytes. Revisions
allow 8,000 new UTF-8 bytes while retaining a 16,000-byte combined message limit.
Dispatch messages allow 32,000 UTF-8 bytes. There may be at most 30 active requests
per device; lists return the newest 200 items.

`GET /health` is public. `POST /pair` accepts `code` and `deviceName`, returning
`deviceToken`. Five failed pairing attempts per connection source in 15 minutes
trigger 429 with `Retry-After: 900`. Reverse-proxied household devices share the
proxy source for this limit. Tokens are returned once and only SHA-256 hashes are
stored. Pairing code and project credential remain only in private configuration.

Device endpoints use `Authorization: Bearer DEVICE_TOKEN`:

* `GET /feedback` returns `{ "items": [...] }` for the owner only.
* `POST /feedback` accepts `clientRequestId`, `title`, `message`, `versionName`,
  and positive integer `versionCode`. It returns the item directly (201 on first
  creation, 200 on a duplicate device/request ID).
* `GET /feedback/{id}` returns the owned item.
* `POST /feedback/{id}/approve` accepts integer `revision`; the exact revision is
  frozen. Repeating approval is idempotent and cannot create a second dispatch.
* `POST /feedback/{id}/revise` accepts `revision` and `message` while awaiting
  approval or after a planning failure. The revision increments and planning restarts.
* `POST /feedback/{id}/reopen` accepts `message` after `planning_failed`, `failed`,
  `published`, or `resolved`. This explicit user action creates a new revision and
  revokes old callbacks. An uncertain dispatch cannot be reopened: its original task
  may still be running and requires its existing callback or operator reconciliation.
* `POST /installed` accepts `versionName` and `versionCode`, returning `{ "ok": true }`.
  It resolves only owned feedback with an independently verified published release
  matching **both fields exactly**. A receipt arriving before the publication callback
  is retained and checked after release verification. A larger version code alone
  does not prove that a fix is included.

Items contain `id`, `title`, `message`, `status`, `revision`, nullable `plan`,
`statusMessage`, nullable `sessionUrl`, nullable `releaseVersion`, nullable
`releaseVersionCode`, `createdAt`, and `updatedAt`. The plan contains `summary`,
`changes`, `acceptance`, and `risks`.

## Dispatch and callbacks

Normal states are `planning → awaiting_approval → dispatching → executing → testing
→ published → resolved`. Failure states are `planning_failed`, `dispatch_uncertain`,
and `failed`. A single worker performs planning and dispatching. Planning and queued
approved jobs survive restart. The approval transaction writes a unique run before
any network request. An interrupted in-flight dispatch becomes `dispatch_uncertain`.
Only one remote run may be sending, accepted, executing, testing, or uncertain for
the project at a time. Additional approvals remain durably queued; planning can
continue. A verified published or failed callback releases the project for the next
queued run. An uncertain run keeps the gate closed until its outcome is reconciled.
No automatic retry is made after a timeout, transport ambiguity, malformed accepted
response, non-201 success, or server error. Explicit rejection can become `failed`.
The creation API has no status read or idempotency key; 201 means accepted, never
verified, published, or resolved.

The remote task receives a new scoped run token, the approved plan and acceptance
criteria, the repository, build/publish requirements, and callback instructions.
The dispatch credential is never sent in that task. Scoped tokens are stored hashed
and cannot access device endpoints or another run.

`POST /runs/{runId}/events` uses `Authorization: Bearer RUN_TOKEN` and accepts:

```json
{"status":"executing"}
```

Then `testing`, `failed` (optional `message`), or `published`. Publishing requires
`versionName`, positive integer `versionCode`, `releaseUrl`, lowercase full 40-digit
`commit`, lowercase 64-digit APK `sha256`, and nonempty `testSummary`. Testing must
precede publishing. Identical retries are idempotent; stale revisions and backward
transitions are rejected. A known uncertain run can still report executing and
continue with its original token, avoiding a second dispatch.

The server independently checks the stable `vVERSION` GitHub Release in
`string1225/ai-pet`, the tag's commit, the `update.json` release asset, canonical APK
asset URL, version/code/sourceCommit equality, and the SHA-256 of downloaded APK
bytes. When the manifest includes `planIds`, it must include the feedback ID.
Existing releases may omit `planIds`. The generated task explicitly passes
`tools/publish_release.py --plan-id FEEDBACK_ID` for future releases.

Each verified publication atomically stores `versionName`, `versionCode`,
`releaseUrl`, `commit`, `sha256`, `testSummary`, and `verifiedAt` in the run's
`evidence_json` column. Startup safely adds that nullable column to older databases;
existing jobs and states are preserved, with no invented evidence for older rows.

The only accepted release URLs are the fixed GitHub repository's canonical tag
URLs. Downloads use fixed GitHub URLs and can fall back to the fixed household
release mirror at `/github-release/string1225/ai-pet/download/TAG/ASSET`;
client-provided arbitrary URLs are never fetched. Missing or
unavailable proof keeps the request out of `published`; retry a 503 callback after
the release and mirror are available. GitHub metadata is read through the public
API, so its anonymous rate limit can temporarily delay verification.

The root repository's `tools/report_feedback.py` reads `AI_PET_RUN_TOKEN` from its
environment and accepts the flags documented in the task message. Tokens must not
be passed in command-line flags, committed, or printed.

## Operations

Back up the database with SQLite's backup API or stop the service before copying
the database and WAL together. Keep private configuration separately. Pairing a
new device creates a separate owner; it does not expose earlier devices' requests.
To recover a lost phone's access, restore its locally stored device token or use an
operator-controlled data migration. There is no unauthenticated recovery endpoint.

The server limits simultaneous HTTP request handlers, input size, socket read
timeouts, planner output, remote output, and release downloads. It deliberately
does not log request URLs, bodies, authorization headers, child output, or raw
network exceptions. Systemd retains service lifecycle logs without credentials.
