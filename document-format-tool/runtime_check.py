from __future__ import annotations

"""Startup probe for the Python and optional Microsoft Word runtime."""

import argparse
import json
import os
import sys


def check_runtime(check_legacy: bool) -> dict[str, str | bool]:
    import docx  # noqa: F401
    import openai  # noqa: F401

    result: dict[str, str | bool] = {
        "success": True,
        "python": sys.executable,
        "legacyChecked": check_legacy,
    }
    if not check_legacy:
        return result
    if os.name != "nt":
        raise RuntimeError("旧版 .doc/.dotx 转换仅支持 Windows + Microsoft Word")

    import win32com.client

    word = None
    try:
        word = win32com.client.DispatchEx("Word.Application")
        word.Visible = False
        word.DisplayAlerts = 0
        result["wordVersion"] = str(word.Version)
    finally:
        if word is not None:
            word.Quit()
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Dokiai Word 格式运行环境检查")
    parser.add_argument(
        "--legacy",
        action="store_true",
        help="同时验证 Microsoft Word COM 是否可用于旧版模板转换",
    )
    args = parser.parse_args()
    try:
        print(json.dumps(check_runtime(args.legacy), ensure_ascii=False))
        return 0
    except Exception as exception:
        print(str(exception), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
