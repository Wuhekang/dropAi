from __future__ import annotations

from contextlib import contextmanager
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import time
from typing import Iterator


WD_DO_NOT_SAVE_CHANGES = 0
WD_FORMAT_DOCUMENT_DEFAULT = 16
WD_ALERTS_NONE = 0
MSO_AUTOMATION_SECURITY_FORCE_DISABLE = 3
COM_SERVER_EXECUTION_FAILED = -2146959355


class WordConversionError(RuntimeError):
    """无法将旧版 Word 文件安全转为临时 DOCX。"""


class WordDocumentConverter:
    """为 python-docx 准备可读的 DOCX 路径。

    `.docx` 直接返回原路径；`.doc` 和 `.dotx` 在 Windows 上使用独立
    Word COM 进程读取，仅把转换结果写入临时目录。上下文退出后临时文件
    会被删除，原文件从不会被保存或覆盖。
    """

    SUPPORTED_SUFFIXES = frozenset({".doc", ".docx", ".dotx"})

    def __init__(
        self,
        dispatch_ex=None,
        pythoncom_module=None,
        *,
        startup_attempts: int = 2,
        startup_retry_delay: float = 0.5,
        sleep=time.sleep,
    ) -> None:
        # 依赖注入便于在未安装 Word 的环境中验证 COM 生命周期。
        self._dispatch_ex = dispatch_ex
        self._pythoncom = pythoncom_module
        self._startup_attempts = max(1, startup_attempts)
        self._startup_retry_delay = max(0.0, startup_retry_delay)
        self._sleep = sleep

    @staticmethod
    def _is_transient_startup_error(exc: Exception) -> bool:
        """只识别 Word COM 的“服务器运行失败”，避免重试真实文件错误。"""

        hresult = getattr(exc, "hresult", None)
        if hresult is None and exc.args:
            hresult = exc.args[0]
        return hresult == COM_SERVER_EXECUTION_FAILED

    def _start_isolated_word(self, dispatch_ex):
        """启动本次转换独享的 Word；瞬态启动失败时做一次短暂重试。"""

        for attempt in range(1, self._startup_attempts + 1):
            try:
                # DispatchEx 始终新建实例，不会附着或关闭用户现有的 Word。
                return dispatch_ex("Word.Application")
            except Exception as exc:
                if (
                    attempt >= self._startup_attempts
                    or not self._is_transient_startup_error(exc)
                ):
                    raise
                self._sleep(self._startup_retry_delay)

    @contextmanager
    def as_docx(self, path: str | Path) -> Iterator[Path]:
        source = Path(path).expanduser().resolve()
        self._validate_source(source)
        if source.suffix.lower() == ".docx":
            yield source
            return

        # ignore_cleanup_errors 可防止 Word/防病毒软件短暂持有文件句柄时掩盖
        # 已完成的提取结果；Word 本身仍会在 yield 前关闭。
        with TemporaryDirectory(prefix="word_formatter_", ignore_cleanup_errors=True) as temp_dir:
            converted = Path(temp_dir) / f"{source.stem}.docx"
            self._convert_with_word(source, converted)
            if not converted.is_file() or converted.stat().st_size == 0:
                raise WordConversionError("Word 未生成有效的临时 DOCX 文件")
            yield converted

    @classmethod
    def _validate_source(cls, source: Path) -> None:
        if not source.is_file():
            raise FileNotFoundError(f"找不到 Word 文件：{source}")
        if source.suffix.lower() not in cls.SUPPORTED_SUFFIXES:
            raise ValueError("模板识别仅支持 .doc、.docx 或 .dotx 文件")

    def _load_com(self):
        if self._dispatch_ex is not None and self._pythoncom is not None:
            return self._dispatch_ex, self._pythoncom
        try:
            import pythoncom  # type: ignore[import-not-found]
            from win32com.client import DispatchEx  # type: ignore[import-not-found]
        except ImportError as exc:  # pragma: no cover - 取决于目标机环境
            raise WordConversionError(
                "读取 .doc/.dotx 需要 Windows、Microsoft Word 和 pywin32"
            ) from exc
        return DispatchEx, pythoncom

    def _convert_with_word(self, source: Path, output: Path) -> None:
        if os.name != "nt":
            raise WordConversionError(".doc/.dotx 转换仅支持已安装 Microsoft Word 的 Windows")

        dispatch_ex, pythoncom = self._load_com()
        word = None
        document = None
        com_initialized = False
        try:
            pythoncom.CoInitialize()
            com_initialized = True
            # DispatchEx 强制使用独立 Word 实例，不会复用或关闭用户已打开的 Word。
            word = self._start_isolated_word(dispatch_ex)
            word.Visible = False
            word.DisplayAlerts = WD_ALERTS_NONE
            try:
                word.AutomationSecurity = MSO_AUTOMATION_SECURITY_FORCE_DISABLE
            except Exception:
                # 部分老版 Word/WPS COM 不暴露该属性，但仍可继续以只读方式打开。
                pass
            try:
                # Word 的 Documents.Open 不提供 Excel 风格的 UpdateLinks 参数；
                # 应在打开前关闭应用级自动链接更新，避免模板访问外部资源。
                word.Options.UpdateLinksAtOpen = False
            except Exception:
                # 极老版本可能不暴露此选项，仍以只读、禁宏模式继续。
                pass
            document = word.Documents.Open(
                FileName=str(source),
                ConfirmConversions=False,
                ReadOnly=True,
                AddToRecentFiles=False,
                Revert=False,
                Visible=False,
                OpenAndRepair=False,
                NoEncodingDialog=True,
            )
            document.SaveAs2(
                FileName=str(output),
                FileFormat=WD_FORMAT_DOCUMENT_DEFAULT,
                AddToRecentFiles=False,
            )
        except WordConversionError:
            raise
        except Exception as exc:
            raise WordConversionError(f"Word 只读转换失败：{exc}") from exc
        finally:
            if document is not None:
                try:
                    document.Close(SaveChanges=WD_DO_NOT_SAVE_CHANGES)
                except Exception:
                    pass
                document = None
            if word is not None:
                try:
                    word.Quit(SaveChanges=WD_DO_NOT_SAVE_CHANGES)
                except Exception:
                    pass
                word = None
            if com_initialized:
                try:
                    pythoncom.CoUninitialize()
                except Exception:
                    pass

    def update_fields_in_place(self, path: str | Path) -> None:
        """Refresh TOC/page fields in a newly-created output document."""
        target = Path(path).expanduser().resolve()
        if os.name != "nt":
            raise WordConversionError("目录自动刷新仅支持已安装 Microsoft Word 的 Windows")
        dispatch_ex, pythoncom = self._load_com()
        word = None
        document = None
        initialized = False
        try:
            pythoncom.CoInitialize()
            initialized = True
            word = self._start_isolated_word(dispatch_ex)
            word.Visible = False
            word.DisplayAlerts = WD_ALERTS_NONE
            document = word.Documents.Open(
                FileName=str(target), ReadOnly=False, AddToRecentFiles=False,
                Visible=False, OpenAndRepair=True,
            )
            document.Repaginate()
            document.Fields.Update()
            for index in range(1, document.TablesOfContents.Count + 1):
                document.TablesOfContents.Item(index).Update()
            document.Repaginate()
            document.Save()
        except Exception as exc:
            raise WordConversionError(f"Word 目录域刷新失败：{exc}") from exc
        finally:
            if document is not None:
                try:
                    document.Close(SaveChanges=WD_DO_NOT_SAVE_CHANGES)
                except Exception:
                    pass
            if word is not None:
                try:
                    word.Quit(SaveChanges=WD_DO_NOT_SAVE_CHANGES)
                except Exception:
                    pass
            if initialized:
                try:
                    pythoncom.CoUninitialize()
                except Exception:
                    pass


@contextmanager
def readable_docx(path: str | Path) -> Iterator[Path]:
    """便捷入口：在上下文内返回可由 python-docx 读取的路径。"""

    with WordDocumentConverter().as_docx(path) as converted:
        yield converted
