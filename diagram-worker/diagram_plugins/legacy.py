from dataclasses import asdict,is_dataclass
import importlib,shutil
from pathlib import Path
from diagram_core.registry import PLUGIN_REGISTRY,CANONICAL,DISPLAY
from diagram_core.types import DiagramType
SPECS={DiagramType.FUNCTION_MODULE:("function_module_diagram_desktop","三级功能模块图_输入模板.txt"),DiagramType.FLOWCHART:("flowchart_diagram_desktop","标准程序流程图_输入模板.txt"),DiagramType.ER_DIAGRAM:("database_er_diagram_desktop","Chen_ER图_输入模板.txt"),DiagramType.ARCHITECTURE:("system_architecture_diagram_desktop","系统架构图_输入模板.txt"),DiagramType.USE_CASE:("use_case_diagram_desktop","UML用例图_输入模板.txt"),DiagramType.BLOCK_DIAGRAM:("system_block_diagram_desktop","系统框图_输入模板.txt"),DiagramType.SEQUENCE:("sequence_diagram_desktop","UML时序图_输入模板.txt")}
class LegacyPlugin:
 def __init__(self,dtype,module_name,template_name):self.diagram_type=dtype;self.display_name=DISPLAY[dtype];self.canonical_header=CANONICAL[dtype];self.module=importlib.import_module(module_name);self.app=self.module.App;self.host=object.__new__(self.app);self.template_name=template_name
 def parse(self,text):return self.host.parse(text)
 def validate(self,document):return []
 def layout(self,document,viewport=None):return self.host.make_layout(document)
 def fill_tree(self,tree,document):self.host.tree=tree;return self.host.fill_tree(document)
 def draw_canvas(self,canvas,document,layout):return self.host.draw(canvas,document,layout)
 def export_vsdx(self,document,layout,out):
  """Run the legacy Visio exporter in isolation; its PNG is not the unified PNG."""
  out=Path(out);tmp=out/".visio_export";tmp.mkdir(parents=True,exist_ok=True)
  try:
   result=self.host.export_visio(document,layout,tmp);vsdx=Path(result[0] if isinstance(result,(tuple,list)) else result);target=out/vsdx.name
   if target.exists():target.unlink()
   shutil.move(str(vsdx),str(target));return target
  finally:
   shutil.rmtree(tmp,ignore_errors=True)
 def get_template(self):return self.canonical_header+"\n"+self.app.template_text.lstrip("\ufeff\n")
 def title(self,document):return self.host.export_title(document)
def register_plugins():
 for dtype,(module,template) in SPECS.items():PLUGIN_REGISTRY[dtype]=LegacyPlugin(dtype,module,template)
 return PLUGIN_REGISTRY
