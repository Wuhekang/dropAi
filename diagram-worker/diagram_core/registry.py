import difflib
from offline_diagram_common import ParseIssue
from .types import DiagramType,HeaderResult
CANONICAL={DiagramType.FUNCTION_MODULE:"@FunctionModule",DiagramType.FLOWCHART:"@Flowchart",DiagramType.ER_DIAGRAM:"@ERDiagram",DiagramType.ARCHITECTURE:"@ArchitectureDiagram",DiagramType.USE_CASE:"@UseCaseDiagram",DiagramType.BLOCK_DIAGRAM:"@BlockDiagram",DiagramType.SEQUENCE:"@SequenceDiagram"}
HEADER_ALIASES={header.lower():dtype for dtype,header in CANONICAL.items()}
DISPLAY={DiagramType.FUNCTION_MODULE:"三级功能模块图",DiagramType.FLOWCHART:"标准程序流程图",DiagramType.ER_DIAGRAM:"Chen ER图",DiagramType.ARCHITECTURE:"系统架构图",DiagramType.USE_CASE:"UML用例图",DiagramType.BLOCK_DIAGRAM:"系统框图",DiagramType.SEQUENCE:"UML时序图"}
PLUGIN_REGISTRY={}
BODY_SIGNATURES={DiagramType.USE_CASE:("[参与者]","[用例]","[关联]"),DiagramType.SEQUENCE:("[参与者]","[消息]"),DiagramType.BLOCK_DIAGRAM:("[节点]","[连接]","|center|"),DiagramType.FLOWCHART:("[节点]","[连接]","|start|"),DiagramType.ARCHITECTURE:("层：","组件："),DiagramType.FUNCTION_MODULE:("系统：","模块：","功能："),DiagramType.ER_DIAGRAM:("实体：","---")}
def strip_block_comments(text):
 """Remove //= ... =// DSL comments while preserving line/column positions."""
 chars=list(text);i=0;inside=False
 while i<len(chars):
  if not inside and text.startswith("//=",i):
   inside=True
   for j in range(i,min(i+3,len(chars))):chars[j]=" "
   i+=3;continue
  if inside and text.startswith("=//",i):
   for j in range(i,min(i+3,len(chars))):chars[j]=" "
   inside=False;i+=3;continue
  if inside and chars[i] not in "\r\n":chars[i]=" "
  i+=1
 return "".join(chars)
def suggest_body_type(text,current):
 text=strip_block_comments(text);scores={dtype:sum(1 for token in tokens if token in text) for dtype,tokens in BODY_SIGNATURES.items()};best=max(scores,key=scores.get)
 return best if best!=current and scores[best]>=2 and scores[best]>scores.get(current,0) else None
def detect_header(text):
 cleaned=strip_block_comments(text.lstrip("\ufeff"));lines=cleaned.splitlines();idx=None;token=None
 for i,line in enumerate(lines):
  value=line.strip().lstrip("\ufeff")
  if value and not value.startswith("#") and not value.startswith("//"):idx=i;token=value;break
 if idx is None or not token.startswith("@"):
  msg="第一条有效语句必须是图形头标记，例如 @FunctionModule。";return HeaderResult(None,None,(idx or 0)+1,text,ParseIssue((idx or 0)+1,"错误","HEADER_MISSING",msg,token or "","增加图形头标记。"))
 dtype=HEADER_ALIASES.get(token.lower())
 if not dtype:
  choices=list(CANONICAL.values());guess=difflib.get_close_matches(token,choices,n=1,cutoff=.35);suggest=f"您是否想输入：{guess[0]}" if guess else "使用七种标准头标记之一。";return HeaderResult(None,None,idx+1,text,ParseIssue(idx+1,"错误","HEADER_UNKNOWN",f"不支持的图形头标记：{token}",token,suggest))
 lines[idx]="";return HeaderResult(dtype,CANONICAL[dtype],idx+1,"\n".join(lines))
