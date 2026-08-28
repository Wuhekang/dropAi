from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime
import json
from pathlib import Path
from typing import Any


@dataclass(slots=True)
class ChangeRecord:
    paragraph_index: int | None
    item: str
    old_value: str
    new_value: str
    reason: str
    status: str = "success"


@dataclass(slots=True)
class ProcessResult:
    source_path: Path
    output_path: Path
    records: list[ChangeRecord] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    started_at: datetime = field(default_factory=datetime.now)

    @property
    def changed_count(self) -> int:
        return sum(record.status == "success" for record in self.records)

    def save_log(self, path: str | Path) -> None:
        payload: dict[str, Any] = {
            "source_path": str(self.source_path),
            "output_path": str(self.output_path),
            "started_at": self.started_at.isoformat(timespec="seconds"),
            "changed_count": self.changed_count,
            "warnings": self.warnings,
            "records": [asdict(record) for record in self.records],
        }
        Path(path).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
