from __future__ import annotations

"""Structured startup probe for the Word-format Python runtime.

The probe intentionally avoids importing the formatter package. That keeps a
missing dependency from producing an import-time traceback before a caller can
receive a stable error code.
"""

import argparse
import importlib
import json
import os
import sys
import time
from typing import Any, Sequence


MINIMUM_PYTHON = (3, 10)
COM_SERVER_EXEC_FAILURE = -2146959355
COM_START_RETRY_SECONDS = 0.5


class RuntimeCheckError(RuntimeError):
    """A runtime problem that can be safely reported without a traceback."""

    def __init__(
        self,
        error_code: str,
        message: str,
        *,
        missing_module: str | None = None,
    ) -> None:
        super().__init__(message)
        self.error_code = error_code
        self.missing_module = missing_module


def _version_tuple(version_info: Sequence[int]) -> tuple[int, int, int]:
    values = list(version_info[:3])
    values.extend([0] * (3 - len(values)))
    return int(values[0]), int(values[1]), int(values[2])


def _base_result(
    *,
    check_legacy: bool,
    check_doubao: bool,
    version_info: Sequence[int],
) -> dict[str, Any]:
    version = _version_tuple(version_info)
    return {
        "success": True,
        "python": sys.executable,
        "pythonVersion": ".".join(str(part) for part in version),
        "doubaoChecked": check_doubao,
        "legacyChecked": check_legacy,
    }


def _require_python(version_info: Sequence[int]) -> None:
    version = _version_tuple(version_info)
    if version[:2] < MINIMUM_PYTHON:
        raise RuntimeCheckError(
            "PYTHON_UNSUPPORTED",
            "Word 格式处理要求 Python 3.10 或更高版本。",
        )


def _import_required(module_name: str, package_name: str) -> Any:
    try:
        return importlib.import_module(module_name)
    except (ImportError, ModuleNotFoundError) as exception:
        missing = getattr(exception, "name", None) or module_name
        raise RuntimeCheckError(
            "MISSING_DEPENDENCY",
            f"Word 格式处理缺少 {package_name} 依赖，请安装 requirements-web.txt 后重试。",
            missing_module=str(missing),
        ) from None


def _check_core_docx() -> None:
    docx = _import_required("docx", "python-docx")
    if not callable(getattr(docx, "Document", None)):
        raise RuntimeCheckError(
            "INVALID_DEPENDENCY",
            "当前 docx 模块不是可用的 python-docx，请重新安装 requirements-web.txt。",
            missing_module="docx.Document",
        )
    _import_required("docx.oxml.ns", "python-docx")


def _check_doubao() -> None:
    openai = _import_required("openai", "openai")
    if not callable(getattr(openai, "OpenAI", None)):
        raise RuntimeCheckError(
            "INVALID_DEPENDENCY",
            "当前 openai 模块不完整，请重新安装 requirements-web.txt。",
            missing_module="openai.OpenAI",
        )
    api_key = (
        os.getenv("ARK_API_KEY", "").strip()
        or os.getenv("DOUBAO_API_KEY", "").strip()
    )
    if not api_key:
        raise RuntimeCheckError(
            "DOUBAO_API_KEY_MISSING",
            "启用豆包解析前，请先配置 ARK_API_KEY 或 DOUBAO_API_KEY。",
        )


def _com_hresult(exception: BaseException) -> int | None:
    for attribute in ("hresult", "winerror"):
        value = getattr(exception, attribute, None)
        if isinstance(value, int):
            return value
    for value in getattr(exception, "args", ()):
        if isinstance(value, int):
            return value
    return None


def _dispatch_word(client: Any) -> Any:
    for attempt in range(2):
        try:
            return client.DispatchEx("Word.Application")
        except Exception as exception:
            transient = _com_hresult(exception) == COM_SERVER_EXEC_FAILURE
            if transient and attempt == 0:
                time.sleep(COM_START_RETRY_SECONDS)
                continue
            raise RuntimeCheckError(
                "WORD_COM_UNAVAILABLE",
                "Microsoft Word COM 启动失败，请确认桌面版 Microsoft Word 已安装且当前账户可以启动 Word。",
            ) from None
    raise AssertionError("unreachable")


def _check_legacy_word() -> str:
    if os.name != "nt":
        raise RuntimeCheckError(
            "LEGACY_UNSUPPORTED",
            "旧版 .doc/.dotx 转换仅支持 Windows 和桌面版 Microsoft Word。",
        )

    client = _import_required("win32com.client", "pywin32")
    word = _dispatch_word(client)
    try:
        word.Visible = False
        word.DisplayAlerts = 0
        return str(word.Version)
    except Exception:
        raise RuntimeCheckError(
            "WORD_COM_UNAVAILABLE",
            "Microsoft Word COM 无法完成运行环境检查，请确认当前账户可以正常启动 Word。",
        ) from None
    finally:
        try:
            word.Quit()
        except Exception:
            # The instance was created by this probe. Cleanup is best effort and
            # must never target or close a Word process owned by the user.
            pass


def check_runtime(
    check_legacy: bool,
    check_doubao: bool = False,
    *,
    version_info: Sequence[int] | None = None,
) -> dict[str, Any]:
    effective_version = version_info if version_info is not None else sys.version_info
    _require_python(effective_version)
    _check_core_docx()
    if check_doubao:
        _check_doubao()

    result = _base_result(
        check_legacy=check_legacy,
        check_doubao=check_doubao,
        version_info=effective_version,
    )
    if check_legacy:
        result["wordVersion"] = _check_legacy_word()
    return result


def _failure_payload(
    exception: BaseException,
    *,
    check_legacy: bool,
    check_doubao: bool,
) -> dict[str, Any]:
    payload = _base_result(
        check_legacy=check_legacy,
        check_doubao=check_doubao,
        version_info=sys.version_info,
    )
    payload["success"] = False
    if isinstance(exception, RuntimeCheckError):
        payload["errorCode"] = exception.error_code
        payload["message"] = str(exception)
        if exception.missing_module:
            payload["missingModule"] = exception.missing_module
    else:
        payload["errorCode"] = "RUNTIME_CHECK_FAILED"
        payload["message"] = "Word 格式处理运行环境检查失败，请查看服务器日志。"
    return payload


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Dokiai Word 格式运行环境检查")
    parser.add_argument(
        "--doubao",
        action="store_true",
        help="同时验证可选的豆包/openai Python 依赖",
    )
    parser.add_argument(
        "--legacy",
        action="store_true",
        help="同时验证 Microsoft Word COM 是否可用于旧版模板转换",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        payload = check_runtime(args.legacy, args.doubao)
        exit_code = 0
    except Exception as exception:
        payload = _failure_payload(
            exception,
            check_legacy=args.legacy,
            check_doubao=args.doubao,
        )
        exit_code = 1
    print(json.dumps(payload, ensure_ascii=False))
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
