import argparse
import unittest
from report_feedback import payload


class CallbackEvidenceTest(unittest.TestCase):
    def args(self, **changes):
        values = dict(status="published", message="", version_name="0.4.0", version_code=6,
                      release_url="https://github.com/string1225/ai-pet/releases/tag/v0.4.0",
                      commit="a" * 40, sha256="b" * 64, test_summary="unit/lint/release verified")
        return argparse.Namespace(**(values | changes))

    def test_release_evidence_cannot_point_at_another_repository(self):
        with self.assertRaises(ValueError):
            payload(self.args(release_url="https://github.com/other/repo/releases/tag/v0.4.0"))

    def test_unverified_completion_cannot_claim_publication(self):
        with self.assertRaises(ValueError):
            payload(self.args(test_summary=""))

    def test_published_contains_machine_readable_identity(self):
        self.assertEqual(payload(self.args())["versionCode"], 6)
        self.assertEqual(payload(self.args())["commit"], "a" * 40)

    def test_failure_has_bounded_message_without_release_claim(self):
        result = payload(self.args(status="failed", message="x" * 2000))
        self.assertEqual(len(result["message"]), 1000)
        self.assertNotIn("releaseUrl", result)


if __name__ == "__main__":
    unittest.main()
