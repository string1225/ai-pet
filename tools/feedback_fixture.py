#!/usr/bin/env python3
"""Loopback-only feedback fixture; it cannot dispatch tasks or publish releases."""
import argparse
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from backend.feedback_server import Config, FeedbackService, create_server


class Planner:
    def plan(self, item):
        return {"summary": "按家庭建议改进应用", "changes": ["完善建议中的功能"],
                "acceptance": ["旧存档保留，界面和功能通过测试"], "risks": ["安卓安装仍需家长确认"]}


class Dispatcher:
    def dispatch(self, title, message):
        return {"sessionId": "fixture-session", "taskId": "fixture-task",
                "url": "https://codex.sunny-string.cn/agents/#session=fixture-session"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8877)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="ai-pet-feedback-fixture-") as directory:
        config = Config(database=str(Path(directory) / "fixture.sqlite3"), pairing_code="TEST-FAMILY-2026",
                        project_session_url="https://example.invalid/project/sessions", project_token="fixture-only-token",
                        public_base_url="https://example.invalid/ai-pet-feedback", repository=str(Path.cwd()),
                        listen=f"127.0.0.1:{args.port}", path_prefix="/ai-pet-feedback")
        service = FeedbackService(config, planner=Planner(), dispatcher=Dispatcher())
        server = create_server(service)
        service.start()
        try:
            print("Feedback fixture ready; no real tasks or releases", flush=True)
            server.serve_forever()
        finally:
            server.server_close(); service.close()


if __name__ == "__main__":
    main()
