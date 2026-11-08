# Single-family feedback, approved execution and updates

The APK and supporting service are implemented in the current development session. The provided Remote Agent project-session endpoint is exclusively the runtime delivery mechanism for changes that a parent approves inside the app. Its credential belongs only in private server configuration.

## Product flow

The parent pairs a device with a single-family service, submits an idea and views its plan. A read-only Codex process on 139 creates a structured plan against the application repository. Parents may supplement the request, approve the exact revision, inspect progress, and reopen a resolved request. Approval creates at most one dispatch record for that revision and submits a new external project session with execute=true. No Remote Agent protocol changes are needed.

States: planning, awaiting_approval, dispatching, executing, testing, published, resolved; exceptional states planning_failed, dispatch_uncertain, failed. A successful HTTP 201 means accepted only. The external API has neither a result-query capability nor a deduplication key: uncertain delivery is never automatically repeated. A scoped per-run callback credential, independent of the project credential, reports progress and release evidence. Published callbacks are verified against the actual stable GitHub Release and APK digest before publication is recorded. A paired device reporting a version containing the fix resolves its own request; reopening is always possible.

## Boundaries

- Android: native Java feedback controller/client, protected by the existing parent session. App data is separate from pet/family saves. Only feedback and explicitly described device/version metadata are sent. Pairing code exchanged once for a device token; secrets are never compiled into the APK.
- Service: dependency-free Python HTTP API, SQLite durable state, one planner/dispatcher worker, bounded requests, fixed endpoint configuration, server-side secrets, token-redacted errors. Runs behind existing HTTPS nginx on 139. Pairing and callback routes use their own authentication. Approval compares revision transactionally.
- Planner: codex exec --sandbox read-only, bounded timeout, JSON schema output; approved code changes run only on the configured Remote Agent project. A planner failure is displayed and can be retried by supplementing a request.
- Dispatch: use POST at the configured project-session endpoint with title/message/execute. Store sessionId/taskId/url from a 201 response. Freeze approved content; task text includes exact acceptance criteria, app repo identity, build/test/publish instructions and scoped callback contract. On timeout or ambiguous upstream failure persist dispatch_uncertain.
- Publishing: preserve the existing signing certificate, require a clean pushed source revision, publish immutable versioned APK, SHA256SUMS and update metadata. The current session verifies and publishes the initial implementation; future external sessions use the same tools.
- Updating: retain existing metadata mirror and verification, add repository-scoped gh-proxy APK download. Default remains manual. Parents may opt into automatic foreground checks and download; no installer opens automatically. Parent gate and Android install confirmation remain mandatory.

## HTTP contract

Base: https://home.sunny-string.cn/ai-pet-feedback

POST /pair {code, deviceName} -> {deviceToken}; GET /feedback -> {items}; POST /feedback {clientRequestId,title,message,versionName,versionCode} -> item; GET /feedback/{id} -> item; POST /feedback/{id}/approve {revision} -> item; POST /feedback/{id}/revise {revision,message} -> item; POST /feedback/{id}/reopen {message} -> item; POST /installed {versionName,versionCode} -> {ok}. All except pairing and health require a paired-device Bearer token.

Item fields: id,title,message,status,revision,plan (null or {summary,changes:[string],acceptance:[string],risks:[string]}),statusMessage,sessionUrl,releaseVersion,releaseVersionCode,createdAt,updatedAt. Ownership is per paired device. Mutation conflicts return HTTP 409; validation 400; authentication 401; unavailable service 503.

POST /runs/{runId}/events with a run-scoped Bearer token accepts status executing/testing/failed/published. Published requires versionName,versionCode,releaseUrl,commit,sha256,testSummary and real Release verification. Parent authenticated list/detail can refresh status without access to the project token. Device installed reporting must not imply that an arbitrary later version contains a fix: published records retain the verified release/commit relationship.

## Verification

Backend tests cover ownership/auth, revision races, duplicate approval, uncertain dispatch without retry, callback auth, actual-release validation and installation resolution. JVM tests cover proxy URL restrictions and update scheduling. Android emulator integration covers pairing/submitting/detail/parent approval against a fixture without real remote changes. Signed APK identity and public health/download are checked before delivery. Existing learning/care/family tests continue to pass.
