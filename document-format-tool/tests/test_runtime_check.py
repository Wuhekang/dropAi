from __future__ import annotations

import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch


TOOL_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL_ROOT))

import runtime_check  # noqa: E402


class FakeComError(Exception):
    def __init__(self, hresult: int) -> None:
        super().__init__(hresult, "simulated COM failure")
        self.hresult = hresult


class FakeWord:
    def __init__(self) -> None:
        self.Visible = True
        self.DisplayAlerts = 1
        self.Version = "16.0"
        self.Quit = Mock()


class RuntimeCheckTests(unittest.TestCase):
    def test_rejects_python_older_than_310(self) -> None:
        with patch.object(runtime_check, "_check_core_docx") as core_check:
            with self.assertRaises(runtime_check.RuntimeCheckError) as raised:
                runtime_check.check_runtime(False, version_info=(3, 9, 18))

        self.assertEqual(raised.exception.error_code, "PYTHON_UNSUPPORTED")
        core_check.assert_not_called()

    def test_core_check_reports_missing_python_docx(self) -> None:
        missing = ModuleNotFoundError("No module named 'docx'")
        missing.name = "docx"
        with patch.object(runtime_check.importlib, "import_module", side_effect=missing):
            with self.assertRaises(runtime_check.RuntimeCheckError) as raised:
                runtime_check.check_runtime(False, version_info=(3, 10, 8))

        self.assertEqual(raised.exception.error_code, "MISSING_DEPENDENCY")
        self.assertEqual(raised.exception.missing_module, "docx")
        self.assertIn("python-docx", str(raised.exception))

    def test_openai_is_checked_only_when_doubao_is_requested(self) -> None:
        with (
            patch.object(runtime_check, "_check_core_docx"),
            patch.object(runtime_check, "_check_doubao") as doubao_check,
        ):
            payload = runtime_check.check_runtime(False, version_info=(3, 10, 8))
            doubao_check.assert_not_called()

            runtime_check.check_runtime(
                False,
                True,
                version_info=(3, 10, 8),
            )
            doubao_check.assert_called_once_with()

        self.assertFalse(payload["doubaoChecked"])

    def test_doubao_check_reports_missing_openai(self) -> None:
        missing = ModuleNotFoundError("No module named 'openai'")
        missing.name = "openai"
        with patch.object(runtime_check.importlib, "import_module", side_effect=missing):
            with self.assertRaises(runtime_check.RuntimeCheckError) as raised:
                runtime_check._check_doubao()

        self.assertEqual(raised.exception.error_code, "MISSING_DEPENDENCY")
        self.assertEqual(raised.exception.missing_module, "openai")

    def test_doubao_check_requires_api_key(self) -> None:
        openai = Mock(OpenAI=Mock())
        with (
            patch.object(runtime_check, "_import_required", return_value=openai),
            patch.dict(runtime_check.os.environ, {}, clear=True),
        ):
            with self.assertRaises(runtime_check.RuntimeCheckError) as raised:
                runtime_check._check_doubao()

        self.assertEqual(raised.exception.error_code, "DOUBAO_API_KEY_MISSING")

    def test_doubao_check_accepts_supported_api_key_names(self) -> None:
        openai = Mock(OpenAI=Mock())
        for variable in ("ARK_API_KEY", "DOUBAO_API_KEY"):
            with self.subTest(variable=variable), patch.object(
                runtime_check, "_import_required", return_value=openai
            ), patch.dict(runtime_check.os.environ, {variable: "test-key"}, clear=True):
                runtime_check._check_doubao()

    def test_legacy_dispatch_retries_server_execution_failure_once(self) -> None:
        word = FakeWord()
        client = Mock()
        client.DispatchEx.side_effect = [
            FakeComError(runtime_check.COM_SERVER_EXEC_FAILURE),
            word,
        ]

        with (
            patch.object(runtime_check.os, "name", "nt"),
            patch.object(runtime_check, "_import_required", return_value=client),
            patch.object(runtime_check.time, "sleep") as sleep,
        ):
            version = runtime_check._check_legacy_word()

        self.assertEqual(version, "16.0")
        self.assertEqual(client.DispatchEx.call_count, 2)
        sleep.assert_called_once_with(runtime_check.COM_START_RETRY_SECONDS)
        self.assertFalse(word.Visible)
        self.assertEqual(word.DisplayAlerts, 0)
        word.Quit.assert_called_once_with()

    def test_legacy_dispatch_does_not_retry_other_com_failures(self) -> None:
        client = Mock()
        client.DispatchEx.side_effect = FakeComError(-2147024891)

        with (
            patch.object(runtime_check.os, "name", "nt"),
            patch.object(runtime_check, "_import_required", return_value=client),
            patch.object(runtime_check.time, "sleep") as sleep,
        ):
            with self.assertRaises(runtime_check.RuntimeCheckError) as raised:
                runtime_check._check_legacy_word()

        self.assertEqual(raised.exception.error_code, "WORD_COM_UNAVAILABLE")
        client.DispatchEx.assert_called_once_with("Word.Application")
        sleep.assert_not_called()

    def test_legacy_dispatch_retries_no_more_than_once(self) -> None:
        client = Mock()
        client.DispatchEx.side_effect = FakeComError(
            runtime_check.COM_SERVER_EXEC_FAILURE
        )

        with (
            patch.object(runtime_check.os, "name", "nt"),
            patch.object(runtime_check, "_import_required", return_value=client),
            patch.object(runtime_check.time, "sleep") as sleep,
        ):
            with self.assertRaises(runtime_check.RuntimeCheckError):
                runtime_check._check_legacy_word()

        self.assertEqual(client.DispatchEx.call_count, 2)
        sleep.assert_called_once_with(runtime_check.COM_START_RETRY_SECONDS)

    def test_main_prints_structured_failure_without_traceback(self) -> None:
        error = runtime_check.RuntimeCheckError(
            "MISSING_DEPENDENCY",
            "缺少 python-docx。",
            missing_module="docx",
        )
        output = io.StringIO()
        with (
            patch.object(runtime_check, "check_runtime", side_effect=error),
            patch("sys.stdout", output),
        ):
            exit_code = runtime_check.main(["--doubao"])

        payload = json.loads(output.getvalue())
        self.assertEqual(exit_code, 1)
        self.assertFalse(payload["success"])
        self.assertEqual(payload["errorCode"], "MISSING_DEPENDENCY")
        self.assertEqual(payload["missingModule"], "docx")
        self.assertTrue(payload["doubaoChecked"])
        self.assertNotIn("Traceback", output.getvalue())

    def test_main_prints_structured_success(self) -> None:
        success = {
            "success": True,
            "python": "python",
            "pythonVersion": "3.10.8",
            "doubaoChecked": True,
            "legacyChecked": False,
        }
        output = io.StringIO()
        with (
            patch.object(runtime_check, "check_runtime", return_value=success),
            patch("sys.stdout", output),
        ):
            exit_code = runtime_check.main(["--doubao"])

        self.assertEqual(exit_code, 0)
        self.assertEqual(json.loads(output.getvalue()), success)


if __name__ == "__main__":
    unittest.main()
