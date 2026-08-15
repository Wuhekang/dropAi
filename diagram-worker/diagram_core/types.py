from dataclasses import dataclass
from enum import Enum
from typing import Any
class DiagramType(Enum):
 FUNCTION_MODULE="function_module";FLOWCHART="flowchart";ER_DIAGRAM="er_diagram";ARCHITECTURE="architecture";USE_CASE="use_case";BLOCK_DIAGRAM="block_diagram";SEQUENCE="sequence"
@dataclass
class HeaderResult:diagram_type:DiagramType|None;canonical_header:str|None;line_number:int;body_text:str;issue:Any=None
@dataclass
class DiagramSession:diagram_type:DiagramType;header:str;source_text:str;document:Any;layout:Any
