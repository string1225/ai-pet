import http.client
import json
from pathlib import Path
import tempfile
import threading
import unittest
import sqlite3
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing

from backend.feedback_server import Config, FeedbackService, create_server, DispatchUncertain, Unavailable


PLAN = {"summary": "Add a visible pet action", "changes": ["Add an action button"],
        "acceptance": ["Button works offline"], "risks": ["Preserve existing saves"]}


class Planner:
    def plan(self, item):
        return PLAN


class Dispatcher:
    def __init__(self):
        self.calls = []
        self.error = None

    def dispatch(self, title, message):
        self.calls.append((title, message))
        if self.error:
            raise self.error
        return {"sessionId": "session1", "taskId": "task1", "url": "https://codex.example/session1"}


class Verifier:
    def __init__(self):
        self.error = None

    def verify(self, event):
        if self.error:
            raise self.error
        return True


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.config = Config(database=str(Path(self.temp.name) / "db.sqlite"), pairing_code="private-code",
                             project_session_url="https://codex.example/sessions", project_token="not-a-real-token",
                             public_base_url="https://home.example/ai-pet-feedback", repository=self.temp.name)
        self.dispatcher = Dispatcher()
        self.verifier = Verifier()
        self.service = FeedbackService(self.config, planner=Planner(), dispatcher=self.dispatcher,
                                       release_verifier=self.verifier)
        self.server = create_server(self.service, ("127.0.0.1", 0))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.token = self.pair()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.service.close()
        self.temp.cleanup()

    def request(self, method, path, body=None, token=None):
        client = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        client.request(method, path, json.dumps(body) if body is not None else None, headers)
        response = client.getresponse()
        result = response.status, json.loads(response.read())
        client.close()
        return result

    def pair(self):
        status, body = self.request("POST", "/pair", {"code": "private-code", "deviceName": "phone"})
        self.assertEqual(200, status)
        return body["deviceToken"]

    def create(self, request_id="request-1"):
        status, item = self.request("POST", "/feedback", {"clientRequestId": request_id, "title": "Pet action",
            "message": "Please add a pet action", "versionName": "1.0.0", "versionCode": 1}, self.token)
        self.assertIn(status, (200, 201))
        return item

    def prepared(self):
        item = self.create()
        self.assertTrue(self.service.run_one())
        status, item = self.request("GET", "/feedback/" + item["id"], token=self.token)
        self.assertEqual(200, status)
        self.assertEqual("awaiting_approval", item["status"])
        return item

    def approved(self):
        item = self.prepared()
        status, item = self.request("POST", "/feedback/" + item["id"] + "/approve", {"revision": item["revision"]}, self.token)
        self.assertEqual(200, status)
        return item

    def dispatched(self):
        item = self.approved()
        self.assertTrue(self.service.run_one())
        prompt = self.dispatcher.calls[0][1]
        metadata = json.loads(prompt.split("CALLBACK_CONFIGURATION\n", 1)[1].split("\nEND_CALLBACK_CONFIGURATION", 1)[0])
        return item, metadata["runId"], metadata["runToken"]

    def event(self, run_id, token, status, **kwargs):
        return self.request("POST", "/runs/" + run_id + "/events", {"status": status, **kwargs}, token)

    def release(self):
        return {"versionName": "1.2.0", "versionCode": 2,
                "releaseUrl": "https://github.com/string1225/ai-pet/releases/tag/v1.2.0",
                "commit": "a" * 40, "sha256": "b" * 64, "testSummary": "Build and tests passed"}

    def test_auth_ownership_dedup_and_private_tokens(self):
        self.assertEqual(200, self.request("GET", "/health")[0])
        self.assertEqual(401, self.request("GET", "/feedback")[0])
        item = self.create()
        self.assertEqual(item["id"], self.create()["id"])
        other = self.pair()
        self.assertEqual(404, self.request("GET", "/feedback/" + item["id"], token=other)[0])
        self.assertEqual([], self.request("GET", "/feedback", token=other)[1]["items"])
        raw = Path(self.config.database).read_bytes()
        self.assertNotIn(self.token.encode(), raw)
        self.assertNotIn(b"private-code", raw)

    def test_revision_is_required_and_duplicate_approval_dispatches_once(self):
        item = self.prepared()
        path = "/feedback/" + item["id"]
        self.assertEqual(409, self.request("POST", path + "/approve", {"revision": 0}, self.token)[0])
        self.assertEqual(400, self.request("POST", path + "/approve", {"revision": True}, self.token)[0])
        for _ in range(2):
            self.assertEqual(200, self.request("POST", path + "/approve", {"revision": 1}, self.token)[0])
        self.service.run_one()
        self.assertFalse(self.service.run_one())
        self.assertEqual(1, len(self.dispatcher.calls))
        self.assertNotIn(self.config.project_token, self.dispatcher.calls[0][1])
        self.assertIn("tools/publish_release.py --plan-id " + item["id"], self.dispatcher.calls[0][1])
        self.assertIn("First report executing", self.dispatcher.calls[0][1])
        self.assertEqual("executing", self.request("GET", path, token=self.token)[1]["status"])

    def test_revision_reruns_plan_and_rejects_old_approval(self):
        item = self.prepared()
        path = "/feedback/" + item["id"]
        status, revised = self.request("POST", path + "/revise", {"revision": 1, "message": "Make it larger"}, self.token)
        self.assertEqual((200, 2, "planning"), (status, revised["revision"], revised["status"]))
        self.assertEqual(409, self.request("POST", path + "/approve", {"revision": 1}, self.token)[0])
        self.service.run_one()
        self.assertEqual("awaiting_approval", self.request("GET", path, token=self.token)[1]["status"])

    def test_dispatch_timeout_never_retries_even_after_restart(self):
        item = self.approved()
        self.dispatcher.error = DispatchUncertain("timeout")
        self.service.run_one()
        self.assertEqual("dispatch_uncertain", self.request("GET", "/feedback/" + item["id"], token=self.token)[1]["status"])
        self.service.recover()
        self.assertFalse(self.service.run_one())
        self.assertEqual(1, len(self.dispatcher.calls))

    def test_callbacks_prove_publication_and_only_exact_install_resolves(self):
        item, run_id, token = self.dispatched()
        self.assertEqual(401, self.event(run_id, self.token, "testing")[0])
        self.assertEqual(200, self.event(run_id, token, "testing")[0])
        self.assertEqual(400, self.event(run_id, token, "published")[0])
        self.verifier.error = Unavailable("upstream unavailable")
        self.assertEqual(503, self.event(run_id, token, "published", **self.release())[0])
        self.assertEqual("testing", self.request("GET", "/feedback/" + item["id"], token=self.token)[1]["status"])
        self.verifier.error = None
        self.assertEqual(200, self.event(run_id, token, "published", **self.release())[0])
        self.assertEqual(200, self.event(run_id, token, "published", **self.release())[0])
        self.assertEqual(409, self.event(run_id, token, "executing")[0])
        other = self.pair()
        self.request("POST", "/installed", {"versionName": "1.2.0", "versionCode": 2}, other)
        self.request("POST", "/installed", {"versionName": "1.3.0", "versionCode": 3}, self.token)
        self.assertEqual("published", self.request("GET", "/feedback/" + item["id"], token=self.token)[1]["status"])
        self.request("POST", "/installed", {"versionName": "1.2.0", "versionCode": 2}, self.token)
        self.assertEqual("resolved", self.request("GET", "/feedback/" + item["id"], token=self.token)[1]["status"])
        self.assertNotIn(token.encode(), Path(self.config.database).read_bytes())

    def test_publication_persists_verified_evidence_and_duplicate_keeps_it(self):
        item, run_id, token = self.dispatched()
        self.event(run_id, token, "testing")
        self.assertEqual(200, self.event(run_id, token, "published", **self.release())[0])
        with closing(sqlite3.connect(self.config.database)) as db:
            evidence = json.loads(db.execute("SELECT evidence_json FROM runs WHERE id=?", (run_id,)).fetchone()[0])
        for field, value in self.release().items():
            self.assertEqual(value, evidence[field])
        self.assertTrue(evidence["verifiedAt"].endswith("Z"))
        self.assertNotIn(token, json.dumps(evidence))
        self.assertEqual(200, self.event(run_id, token, "published", **self.release())[0])
        with closing(sqlite3.connect(self.config.database)) as db:
            self.assertEqual(evidence, json.loads(db.execute("SELECT evidence_json FROM runs WHERE id=?", (run_id,)).fetchone()[0]))

    def test_existing_database_migrates_runs_without_losing_approved_jobs(self):
        item = self.approved()
        with self.service.transaction():
            # Recreate the pre-evidence schema explicitly; old installations may
            # still have queued, approved work when first starting the new code.
            self.service.db.execute("DROP TABLE run_events")
            self.service.db.execute("ALTER TABLE runs RENAME TO old_runs")
            self.service.db.execute("""CREATE TABLE runs (
                id TEXT PRIMARY KEY, feedback_id TEXT NOT NULL REFERENCES feedback(id), revision INTEGER NOT NULL,
                state TEXT NOT NULL, token_hash TEXT, approved_plan TEXT NOT NULL, approved_message TEXT NOT NULL,
                session_id TEXT, task_id TEXT, created_at TEXT NOT NULL, UNIQUE(feedback_id,revision))""")
            columns = "id,feedback_id,revision,state,token_hash,approved_plan,approved_message,session_id,task_id,created_at"
            self.service.db.execute("INSERT INTO runs(" + columns + ") SELECT " + columns + " FROM old_runs")
            self.service.db.execute("DROP TABLE old_runs")
            self.service.db.execute("""CREATE TABLE run_events (
                run_id TEXT NOT NULL REFERENCES runs(id), event_hash TEXT NOT NULL,
                PRIMARY KEY(run_id,event_hash))""")
        recovered = FeedbackService(self.config, planner=Planner(), dispatcher=self.dispatcher,
                                    release_verifier=self.verifier)
        try:
            fields = {row[1] for row in recovered.db.execute("PRAGMA table_info(runs)")}
            self.assertIn("evidence_json", fields)
            row = recovered.db.execute("SELECT * FROM runs WHERE feedback_id=?", (item["id"],)).fetchone()
            self.assertEqual("queued", row["state"])
            self.assertIsNone(row["evidence_json"])
            self.assertTrue(recovered.run_one())
        finally:
            recovered.close()

    def test_distinct_approved_items_wait_for_active_project_run(self):
        first, run_id, token = self.dispatched()
        second = self.create("second-project-request")
        self.assertTrue(self.service.run_one())  # Planning continues while first executes.
        self.assertEqual(200, self.request("POST", "/feedback/" + second["id"] + "/approve", {"revision": 1}, self.token)[0])
        with ThreadPoolExecutor(max_workers=4) as executor:
            self.assertEqual([False] * 4, list(executor.map(lambda _: self.service.run_one(), range(4))))
        self.assertEqual(1, len(self.dispatcher.calls))
        self.assertEqual("dispatching", self.request("GET", "/feedback/" + second["id"], token=self.token)[1]["status"])
        recovered = FeedbackService(self.config, planner=Planner(), dispatcher=self.dispatcher,
                                    release_verifier=self.verifier)
        try:
            self.assertFalse(recovered.run_one())
        finally:
            recovered.close()
        self.assertEqual(200, self.event(run_id, token, "testing")[0])
        self.assertFalse(self.service.run_one())
        self.assertEqual(200, self.event(run_id, token, "published", **self.release())[0])
        self.assertTrue(self.service.run_one())
        self.assertEqual(2, len(self.dispatcher.calls))

    def test_uncertain_run_blocks_reopen_and_queued_work_until_failed_callback(self):
        item = self.approved()
        self.dispatcher.error = DispatchUncertain("timeout")
        self.service.run_one()
        metadata = json.loads(self.dispatcher.calls[0][1].split("CALLBACK_CONFIGURATION\n", 1)[1].split("\nEND_CALLBACK_CONFIGURATION", 1)[0])
        self.assertEqual(409, self.request("POST", "/feedback/" + item["id"] + "/reopen", {"message": "Try again"}, self.token)[0])
        second = self.create("after-uncertain")
        self.service.run_one()
        self.request("POST", "/feedback/" + second["id"] + "/approve", {"revision": 1}, self.token)
        self.assertFalse(self.service.run_one())
        self.assertEqual(1, len(self.dispatcher.calls))
        self.assertEqual(200, self.event(metadata["runId"], metadata["runToken"], "failed", message="Remote task confirmed stopped")[0])
        self.dispatcher.error = None
        self.assertTrue(self.service.run_one())
        self.assertEqual(2, len(self.dispatcher.calls))

    def test_reopen_revokes_old_callback(self):
        item, run_id, token = self.dispatched()
        self.event(run_id, token, "failed", message="Tests failed")
        status, reopened = self.request("POST", "/feedback/" + item["id"] + "/reopen", {"message": "Please fix failing tests"}, self.token)
        self.assertEqual((200, 2), (status, reopened["revision"]))
        self.assertEqual(409, self.event(run_id, token, "testing")[0])

    def test_pair_rate_limit_and_input_limits(self):
        for _ in range(5):
            self.assertEqual(401, self.request("POST", "/pair", {"code": "bad", "deviceName": "phone"})[0])
        self.assertEqual(429, self.request("POST", "/pair", {"code": "bad", "deviceName": "phone"})[0])
        self.assertEqual(400, self.request("POST", "/feedback", {"clientRequestId": "2", "title": "x" * 81,
            "message": "text", "versionName": "1", "versionCode": 1}, self.token)[0])
        self.assertEqual(413, self.request("POST", "/feedback", {"message": "x" * 70000}, self.token)[0])

    def test_concurrent_approvals_have_one_durable_dispatch(self):
        item = self.prepared()
        def approve(_):
            return self.request("POST", "/feedback/" + item["id"] + "/approve", {"revision": 1}, self.token)[0]
        with ThreadPoolExecutor(max_workers=8) as executor:
            self.assertEqual([200] * 8, list(executor.map(approve, range(8))))
        self.service.run_one()
        self.service.recover()
        self.assertFalse(self.service.run_one())
        self.assertEqual(1, len(self.dispatcher.calls))

    def test_restart_recovers_planning_and_queued_but_never_sending(self):
        item = self.create()
        recovered = FeedbackService(self.config, planner=Planner(), dispatcher=self.dispatcher,
                                    release_verifier=self.verifier)
        try:
            self.assertTrue(recovered.run_one())
            self.assertEqual("awaiting_approval", self.service.get(self.service.authenticate(self.token), item["id"])["status"])
            self.request("POST", "/feedback/" + item["id"] + "/approve", {"revision": 1}, self.token)
            self.assertTrue(recovered.run_one())
            self.assertFalse(recovered.run_one())
            self.assertEqual(1, len(self.dispatcher.calls))
        finally:
            recovered.close()
        item2 = self.create("request-2")
        self.service.run_one()
        self.request("POST", "/feedback/" + item2["id"] + "/approve", {"revision": 1}, self.token)
        with self.service.transaction():
            self.service.db.execute("UPDATE runs SET state='sending' WHERE feedback_id=?", (item2["id"],))
        self.service.recover()
        self.assertFalse(self.service.run_one())
        self.assertEqual("dispatch_uncertain", self.request("GET", "/feedback/" + item2["id"], token=self.token)[1]["status"])

    def test_planner_failure_is_sanitized_and_retry_uses_new_revision(self):
        class BrokenPlanner:
            def plan(self, item):
                raise RuntimeError("DO NOT LEAK secret endpoint or token")
        self.service.planner = BrokenPlanner()
        item = self.create()
        self.service.run_one()
        result = self.request("GET", "/feedback/" + item["id"], token=self.token)[1]
        self.assertEqual("planning_failed", result["status"])
        self.assertNotIn("DO NOT LEAK", json.dumps(result))
        self.assertEqual(200, self.request("POST", "/feedback/" + item["id"] + "/reopen", {"message": "Retry planning"}, self.token)[0])
        self.service.planner = Planner()
        self.service.run_one()
        self.assertEqual("awaiting_approval", self.request("GET", "/feedback/" + item["id"], token=self.token)[1]["status"])

    def test_callback_cannot_publish_before_testing_or_cross_runs(self):
        item, run_id, token = self.dispatched()
        self.assertEqual(409, self.event(run_id, token, "published", **self.release())[0])
        self.assertEqual(401, self.event("a" * 32, token, "testing")[0])
        self.assertEqual(200, self.event(run_id, token, "testing")[0])
        self.assertEqual(200, self.event(run_id, token, "testing")[0])
        self.assertEqual(409, self.event(run_id, token, "executing", message="Late event")[0])

    def test_utf8_limits_and_prefix_routing(self):
        self.config.path_prefix = "/ai-pet-feedback"
        self.assertEqual(200, self.request("GET", "/ai-pet-feedback/health")[0])
        self.assertEqual(400, self.request("POST", "/feedback", {"clientRequestId": "large", "title": "Pet",
            "message": "猫" * 6000, "versionName": "1", "versionCode": 1}, self.token)[0])
        client = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        client.request("POST", "/pair", "{bad", {"Content-Type": "application/json"})
        response = client.getresponse()
        self.assertEqual(400, response.status)
        response.read()
        client.close()

    def test_malformed_unicode_and_callback_status_are_invalid(self):
        self.assertEqual(400, self.request("POST", "/feedback", {"clientRequestId": "broken", "title": "\ud800",
            "message": "Pet", "versionName": "1", "versionCode": 1}, self.token)[0])
        item, run_id, token = self.dispatched()
        self.assertEqual(400, self.request("POST", "/runs/" + run_id + "/events", {"status": []}, token)[0])


if __name__ == "__main__":
    unittest.main()
