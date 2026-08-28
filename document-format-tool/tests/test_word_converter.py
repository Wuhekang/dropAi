from __future__ import annotations

import unittest
from unittest.mock import Mock, patch

from word_formatter.core.word_converter import (
    COM_SERVER_EXECUTION_FAILED,
    WordConversionError,
    WordDocumentConverter,
)


class FakeComError(Exception):
    def __init__(self, hresult: int, message: str) -> None:
        super().__init__(hresult, message)
        self.hresult = hresult


class WordDocumentConverterTest(unittest.TestCase):
    @patch("word_formatter.core.word_converter.os.name", "nt")
    def test_retries_transient_word_server_startup_once(self) -> None:
        word = Mock()
        document = word.Documents.Open.return_value
        dispatch = Mock(
            side_effect=[
                FakeComError(COM_SERVER_EXECUTION_FAILED, "服务器运行失败"),
                word,
            ]
        )
        pythoncom = Mock()
        sleep = Mock()
        converter = WordDocumentConverter(
            dispatch,
            pythoncom,
            startup_retry_delay=0.01,
            sleep=sleep,
        )

        converter._convert_with_word(Mock(), Mock())

        self.assertEqual(2, dispatch.call_count)
        sleep.assert_called_once_with(0.01)
        document.Close.assert_called_once()
        word.Quit.assert_called_once()
        pythoncom.CoInitialize.assert_called_once()
        pythoncom.CoUninitialize.assert_called_once()

    @patch("word_formatter.core.word_converter.os.name", "nt")
    def test_does_not_retry_non_transient_com_failure(self) -> None:
        dispatch = Mock(side_effect=FakeComError(-1, "拒绝访问"))
        pythoncom = Mock()
        sleep = Mock()
        converter = WordDocumentConverter(dispatch, pythoncom, sleep=sleep)

        with self.assertRaisesRegex(WordConversionError, "拒绝访问"):
            converter._convert_with_word(Mock(), Mock())

        dispatch.assert_called_once_with("Word.Application")
        sleep.assert_not_called()
        pythoncom.CoUninitialize.assert_called_once()


if __name__ == "__main__":
    unittest.main()
